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
#include "gb_words.h"
#include "gb_roget.h"
#include "gb_miles.h"
#include "gb_plane.h"
#include "gb_dijk.h"
static void pr_name(v) Vertex*v; {printf("%s ",v->name);}
static void pr_pair(u,v) Vertex*u,*v; {printf("%s-%s\n",u?u->name:"INF",v?v->name:"INF");}
static long hx(v) Vertex*v; {return v->x.I/4;}
static long wt1[]={1,1,1,1,1,1,1,1,1};
static long wt_vector[]={100,-80589,50000,18935,-18935,18935,18935,18935,18935};
int main(){Graph*g;Vertex*u,*v;long d;setvbuf(stdout,NULL,_IONBF,0);
printf("==words_top\n");print_sample(words(50L,NULL,1000L,1L),3);
printf("==words_equal\n");print_sample(words(20L,wt1,0L,7L),0);
printf("==words_bad\n");print_sample(words(100L,wt_vector,70000000L,69L),5);
g=words(5757L,NULL,0L,69L);
printf("==find_word\n");v=find_word("words",NULL);printf("%s\n",v?v->name:"NULL");
v=find_word("zzzzz",pr_name);printf("|%s\n",v?v->name:"NULL");
v=find_word("graph",pr_name);printf("|%s\n",v?v->name:"NULL");
gb_recycle(g);
printf("==roget_small\n");print_sample(roget(100L,2L,500L,3L),10);
printf("==roget_full\n");print_sample(roget(1022L,0L,0L,0L),1000);
printf("==roget_default\n");print_sample(roget(0L,0L,0L,5L),0);
printf("==miles_span_default\n");print_sample(miles(50L,0L,0L,0L,0L,10L,0L),0);
printf("==miles_dist300\n");print_sample(miles(128L,0L,0L,0L,300L,0L,0L),5);
printf("==miles_weighted\n");print_sample(miles(30L,100L,100L,1L,0L,3L,2L),2);
g=miles(10L,0L,0L,0L,0L,0L,4L);printf("==miles_distance=%ld\n",miles_distance(g->vertices,g->vertices+1));print_sample(g,1);
printf("==miles_bad\n");print_sample(miles(10L,200000L,0L,0L,0L,0L,1L),0);
printf("==plane_small\n");print_sample(plane(10L,0L,0L,0L,0L,1L),0);
printf("==plane_inf\n");print_sample(plane(20L,100L,100L,1L,0L,5L),20);
printf("==plane_prob\n");print_sample(plane(30L,500L,500L,1L,300L,7L),3);
printf("==plane_bad\n");print_sample(plane(1L,0L,0L,0L,0L,1L),0);
g=plane(6L,100L,100L,0L,0L,3L);printf("==delaunay\n");delaunay(g,pr_pair);print_sample(g,2);
printf("==plane_miles_small\n");print_sample(plane_miles(20L,0L,0L,0L,0L,0L,1L),0);
printf("==plane_miles_prob\n");print_sample(plane_miles(40L,0L,0L,0L,1L,20000L,9L),40);
g=roget(1022L,0L,0L,0L);
printf("==dijkstra_roget\n");d=dijkstra(g->vertices,g->vertices+2,g,NULL);printf("return=%ld\n",d);print_dijkstra_result(g->vertices+2);
printf("==dijkstra_roget_far\n");d=dijkstra(g->vertices+4,g->vertices+900,g,NULL);printf("return=%ld\n",d);print_dijkstra_result(g->vertices+900);
init_queue=init_128;enqueue=enq_128;requeue=req_128;del_min=del_128;
printf("==dijkstra_128\n");d=dijkstra(g->vertices+4,g->vertices+900,g,NULL);printf("return=%ld\n",d);print_dijkstra_result(g->vertices+900);
init_queue=init_dlist;enqueue=enlist;requeue=reenlist;del_min=del_first;gb_recycle(g);
g=board(3L,0L,0L,0L,1L,0L,1L);printf("==dijkstra_unreachable\n");d=dijkstra(g->vertices+2,g->vertices,g,NULL);printf("return=%ld\n",d);print_dijkstra_result(g->vertices);gb_recycle(g);
g=miles(20L,0L,0L,0L,0L,0L,1L);printf("==dijkstra_heuristic\n");verbose=1;d=dijkstra(g->vertices,g->vertices+19,g,hx);verbose=0;printf("return=%ld\n",d);print_dijkstra_result(g->vertices+19);gb_recycle(g);
g=miles(20L,0L,0L,0L,0L,0L,1L);printf("==dijkstra_verbose_plain\n");verbose=1;d=dijkstra(g->vertices+3,g->vertices+7,g,NULL);verbose=0;printf("return=%ld\n",d);print_dijkstra_result(g->vertices+7);gb_recycle(g);
return 0;}
