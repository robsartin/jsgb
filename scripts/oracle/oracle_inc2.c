#include "gb_graph.h"
#include "gb_io.h"
#include "gb_basic.h"
#include "gb_rand.h"
#include "gb_save.h"
#include "gb_raman.h"
#define is_boolean(v) (((siz_t)(v))<=1)
static void pr_vert();static void pr_arc();static void pr_util();
static void print_sample(g,n) Graph*g;int n;
{printf("\n");
if(g==NULL){printf("Ooops, we just ran into panic code %ld!\n",panic_code);
if(io_errors)printf("(The I/O error code is 0x%lx)\n",(unsigned long)io_errors);}
else{printf("\"%s\"\n%ld vertices, %ld arcs, util_types %s",g->id,g->n,g->m,g->util_types);
pr_util(g->uu,g->util_types[8],0,g->util_types);pr_util(g->vv,g->util_types[9],0,g->util_types);
pr_util(g->ww,g->util_types[10],0,g->util_types);pr_util(g->xx,g->util_types[11],0,g->util_types);
pr_util(g->yy,g->util_types[12],0,g->util_types);pr_util(g->zz,g->util_types[13],0,g->util_types);
printf("\n");printf("V%d: ",n);
if(n>=g->n||n<0)printf("index is out of range!\n");
else{pr_vert(g->vertices+n,1,g->util_types);printf("\n");}
gb_recycle(g);}}
static void pr_vert(v,l,s) Vertex*v;int l;char*s;
{if(v==NULL)printf("NULL");else if(is_boolean(v))printf("ONE");
else{printf("\"%s\"",v->name);pr_util(v->u,s[0],l-1,s);pr_util(v->v,s[1],l-1,s);pr_util(v->w,s[2],l-1,s);
pr_util(v->x,s[3],l-1,s);pr_util(v->y,s[4],l-1,s);pr_util(v->z,s[5],l-1,s);
if(l>0){register Arc*a;for(a=v->arcs;a;a=a->next){printf("\n   ");pr_arc(a,1,s);}}}}
static void pr_arc(a,l,s) Arc*a;int l;char*s;
{printf("->");pr_vert(a->tip,0,s);if(l>0){printf(", %ld",a->len);pr_util(a->a,s[6],l-1,s);pr_util(a->b,s[7],l-1,s);}}
static void pr_util(u,c,l,s) util u;char c;int l;char*s;
{switch(c){case'I':printf("[%ld]",u.I);break;case'S':printf("[\"%s\"]",u.S?u.S:"(null)");break;
case'A':if(l<0)break;printf("[");if(u.A==NULL)printf("NULL");else pr_arc(u.A,l,s);printf("]");break;
case'V':if(l<0)break;printf("[");pr_vert(u.V,l,s);printf("]");default:break;}}
static long dst[]={0x20000000,0x10000000,0x10000000};
int main(){Graph*g,*gg;setvbuf(stdout,NULL,_IONBF,0);
printf("==perms\n");print_sample(perms(1L,1L,1L,0L,0L,0L,0L),3);
printf("==perms_dir\n");print_sample(perms(2L,1L,0L,0L,0L,2L,1L),2);
printf("==binary\n");print_sample(binary(4L,0L,0L),5);
printf("==binary_dir\n");print_sample(binary(5L,4L,1L),3);
printf("==binary_big\n");print_sample(binary(20L,6L,0L),100);
printf("==raman1\n");print_sample(raman(5L,3L,1L,0L),1);
printf("==raman2\n");print_sample(raman(5L,3L,2L,1L),2);
printf("==raman3red\n");print_sample(raman(31L,3L,3L,1L),4);
printf("==board_dir\n");print_sample(board(3L,3L,0L,0L,-2L,0L,1L),4);
printf("==board_wrap\n");print_sample(board(4L,4L,0L,0L,1L,3L,0L),0);
printf("==simplex\n");print_sample(simplex(4L,2L,0L,0L,0L,0L,0L),3);
printf("==simplex_dir\n");print_sample(simplex(3L,-3L,0L,0L,0L,0L,1L),5);
printf("==subsets2\n");print_sample(subsets(3L,-4L,0L,0L,0L,0L,3L,0L),2);
printf("==parts\n");print_sample(parts(6L,0L,0L,0L),4);
printf("==parts_dir\n");print_sample(parts(7L,3L,4L,1L),2);
printf("==complement_dir\n");print_sample(complement(board(3L,0L,0L,0L,1L,0L,1L),0L,0L,1L),1);
printf("==gunion_dir\n");print_sample(gunion(board(3L,0L,0L,0L,1L,0L,1L),board(3L,0L,0L,0L,-2L,0L,1L),0L,1L),0);
printf("==intersection\n");print_sample(intersection(board(3L,3L,0L,0L,-1L,0L,0L),board(3L,3L,0L,0L,-2L,0L,0L),0L,0L),4);
printf("==intersection_dir\n");print_sample(intersection(board(3L,0L,0L,0L,-2L,0L,1L),board(3L,0L,0L,0L,1L,0L,1L),1L,1L),1);
printf("==lines\n");print_sample(lines(board(3L,3L,0L,0L,1L,0L,0L),0L),5);
printf("==lines_dir\n");print_sample(lines(board(3L,0L,0L,0L,-2L,0L,1L),1L),1);
printf("==product_cart\n");print_sample(product(board(2L,0L,0L,0L,1L,0L,0L),board(3L,0L,0L,0L,1L,0L,0L),0L,0L),1);
printf("==product_direct\n");print_sample(product(board(2L,0L,0L,0L,1L,0L,0L),board(3L,0L,0L,0L,1L,0L,0L),1L,0L),1);
printf("==product_strong\n");print_sample(product(board(2L,0L,0L,0L,1L,0L,0L),board(3L,0L,0L,0L,1L,0L,0L),2L,0L),1);
printf("==product_dir\n");print_sample(product(board(2L,0L,0L,0L,1L,0L,1L),board(2L,0L,0L,0L,-2L,0L,1L),1L,1L),0);
printf("==bi_complete\n");print_sample(bi_complete(2L,3L,0L),1);
printf("==wheel\n");print_sample(wheel(5L,1L,0L),0);
printf("==wheel_dir\n");print_sample(wheel(4L,2L,1L),5);
g=board(3L,0L,0L,0L,1L,0L,0L);g->vertices->ind=2;(g->vertices+1)->ind=-1;(g->vertices+2)->ind=-2;
printf("==induced_neg\n");print_sample(induced(g,"x",1L,1L,0L),0);
g=board(2L,0L,0L,0L,1L,0L,0L);g->vertices->ind=IND_GRAPH;g->vertices->subst=board(3L,0L,0L,0L,1L,1L,0L);(g->vertices+1)->ind=1;
printf("==induced_subst\n");print_sample(induced(g,NULL,0L,0L,0L),1);
printf("==random_dir\n");print_sample(random_graph(4L,7L,1L,1L,1L,NULL,NULL,5L,9L,3L),2);
printf("==random_dist\n");print_sample(random_graph(3L,3L,0L,0L,0L,dst,NULL,1L,1L,7L),0);
printf("==random_multi_neg\n");print_sample(random_graph(3L,8L,-1L,1L,0L,NULL,NULL,1L,5L,11L),1);
printf("==random_bigraph\n");print_sample(random_bigraph(2L,3L,5L,0L,NULL,NULL,1L,3L,5L),3);
printf("==random_bad\n");print_sample(random_graph(0L,1L,0L,0L,0L,NULL,NULL,1L,1L,1L),0);
g=board(3L,0L,0L,0L,1L,0L,0L);
printf("==random_lengths=%ld\n",random_lengths(g,0L,-3L,3L,NULL,9L));print_sample(g,1);
g=board(3L,0L,0L,0L,1L,0L,1L);
printf("==random_lengths_dir=%ld\n",random_lengths(g,1L,10L,12L,dst,4L));print_sample(g,0);
printf("==random_lengths_null=%ld\n",random_lengths(NULL,0L,0L,0L,NULL,0L));
g=board(2L,2L,0L,0L,1L,0L,0L);save_graph(g,"oracle_board.gb");gg=restore_graph("oracle_board.gb");
printf("==restore_board\n");print_sample(gg,0);gb_recycle(g);
g=lines(board(3L,0L,0L,0L,1L,0L,0L),0L);g->util_types[0]='Z';g->util_types[1]='Z';save_graph(g,"oracle_lines.gb");
printf("==restore_lines\n");print_sample(restore_graph("oracle_lines.gb"),1);
printf("==lines_k4_v1\n");print_sample(lines(board(4L,0L,0L,0L,-1L,0L,0L),0L),1);
printf("==lines_k4_v5\n");print_sample(lines(board(4L,0L,0L,0L,-1L,0L,0L),0L),5);
printf("==binary_wide\n");print_sample(binary(100L,5L,0L),0);
return 0;}
